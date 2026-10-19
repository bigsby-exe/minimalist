document.addEventListener("DOMContentLoaded", function () {
  "use strict";
  var csrf = document.querySelector('meta[name="csrf-token"]');
  var workspace = document.getElementById("task-workspace");
  var busy = false, armedBin, binTimer;
  var mutations = Promise.resolve();
  var pendingForms = new WeakMap();
  var refreshDay = function () {};
  var status = document.getElementById("task-status");

  function relativeDate(due, today) {
    var days = Math.round((Date.parse(due) - Date.parse(today)) / 86400000);
    if (days === 0) return "Today";
    if (days === 1) return "Tomorrow";
    if (days === -1) return "Yesterday";
    if (days > 1 && days <= 7) return "In " + days + " days";
    if (days < -1 && days >= -7) return -days + " days overdue";
    var options = { day: "numeric", month: "short", timeZone: "UTC" };
    if (due.slice(0, 4) !== today.slice(0, 4)) options.year = "numeric";
    return new Intl.DateTimeFormat("en-GB", options).format(new Date(due));
  }
  function resetBin() {
    clearTimeout(binTimer);
    if (armedBin) {
      armedBin.classList.remove("armed");
      armedBin.setAttribute("aria-label", armedBin.dataset.originalLabel);
    }
    armedBin = null;
  }
  function updatePickers(form) {
    var select = form.querySelector(".tag-picker select");
    if (select) form.querySelector(".selected-emoji").textContent = select.selectedOptions[0].dataset.emoji || "＋";
    var date = form.querySelector('input[type="date"]');
    if (date) {
      form.querySelector(".date-picker").classList.toggle("has-date", !!date.value);
      var label = form.querySelector(".composer-date");
      if (label) label.textContent = date.value ? relativeDate(date.value, workspace.dataset.today) : "";
    }
  }
  function sizeEditor(input) {
    input.style.height = "auto";
    input.style.height = input.scrollHeight + "px";
  }
  function startEditing(row, openDate) {
    row.classList.add("editing", "active");
    var form = row.querySelector(".inline-edit");
    form.hidden = false;
    var input = form.querySelector("textarea");
    input.focus(); sizeEditor(input);
    if (openDate) {
      form.querySelector(".date-picker").open = true;
      form.querySelector('input[type="date"]').focus();
    }
  }
  function finishEditing(form, cancel) {
    if (cancel) { form.reset(); status.textContent = ""; }
    form.hidden = true;
    form.querySelector(".date-picker").open = false;
    var row = form.closest(".task-row");
    row.classList.remove("editing");
    updatePickers(form);
    if (row.dataset.expired === "true") { row.remove(); refreshDay(); }
  }
  function dirty(form) {
    return Array.from(form.elements).some(function (input) {
      if (input.tagName === "SELECT") return Array.from(input.options).some(function (option) { return option.selected !== option.defaultSelected; });
      return "defaultValue" in input && input.value !== input.defaultValue;
    });
  }
  function submitTaskForm(form) {
    if (pendingForms.has(form)) return pendingForms.get(form);
    var result = mutations.then(function () { return saveTaskForm(form); });
    pendingForms.set(form, result);
    mutations = result.finally(function () { pendingForms.delete(form); });
    return result;
  }
  async function saveTaskForm(form) {
    if (!form.reportValidity()) return;
    var editor = form.classList.contains("inline-edit");
    if (editor && !dirty(form)) { finishEditing(form, false); return; }
    var data = new FormData(form);
    if (form.querySelector('[name="task[description]"]') && !data.get("task[description]").trim()) {
      status.textContent = "A task needs a name.";
      form.querySelector('[name="task[description]"]').focus(); return;
    }
    busy = true;
    form.setAttribute("aria-busy", "true");
    var controls = Array.from(form.querySelectorAll("input, select, textarea, button"));
    controls.forEach(function (control) { control.disabled = true; });
    try {
      var response = await fetch(form.action, { method: "POST", headers: { "X-CSRF-Token": csrf.content }, body: data });
      var page = new DOMParser().parseFromString(await response.text(), "text/html");
      if (!response.ok) throw new Error(page.querySelector(".flash.alert")?.textContent || "Could not save. Please try again.");
      var groups = page.getElementById("task-groups");
      if (!groups) throw new Error("Your session may have expired. Reload to sign in; copy any unfinished text first.");
      if (editor) finishEditing(form, false);
      var focused = document.activeElement;
      var focusedRow = focused.closest(".task-row.editing");
      var focusedStar = focused.matches(".star");
      // Preserve another active edit, even when starring or binning another task.
      document.querySelectorAll(".task-row.editing").forEach(function (row) {
        var fresh = groups.querySelector('[data-task-id="' + row.dataset.taskId + '"]');
        if (fresh) {
          row.querySelector(".star-form").replaceWith(fresh.querySelector(".star-form"));
          row.dataset.starredUntil = fresh.dataset.starredUntil;
          delete row.dataset.expired;
          fresh.replaceWith(row);
        } else if (!form.classList.contains("bin-form") || form.closest(".task-row") !== row) {
          // An expired or unstarred task stays visible until its draft is finished.
          row.dataset.expired = "true";
          row.querySelector(".star").textContent = "☆";
          row.querySelector(".star").setAttribute("aria-pressed", "false");
          row.querySelector(".star").setAttribute("aria-label", "Star " + row.querySelector(".task-name").textContent);
          row.querySelector('input[name="starred"]').value = "true";
          var section = row.closest(".tag-section");
          var destination = groups.querySelector('[aria-labelledby="' + section.getAttribute("aria-labelledby") + '"]');
          if (!destination) {
            destination = section.cloneNode(false);
            destination.appendChild(section.querySelector("h2").cloneNode(true));
            var list = document.createElement("ul"); list.className = "task-list";
            destination.appendChild(list); groups.appendChild(destination);
          }
          destination.querySelector("ul").appendChild(row);
          groups.querySelector(".empty")?.remove();
        }
      });
      document.getElementById("task-groups").replaceWith(groups);
      if (focusedRow && focusedRow.isConnected) {
        if (focusedStar) focusedRow.querySelector(".star").focus();
        else if (focused.isConnected) focused.focus();
      }
      groups.querySelectorAll(".inline-edit").forEach(updatePickers);
      if (form.classList.contains("add-task")) {
        form.querySelector('[name="task[description]"]').value = "";
        form.querySelector('input[type="date"]').value = "";
        form.querySelector(".date-picker").open = false;
        updatePickers(form);
      }
      status.textContent = "";
      document.querySelectorAll("main > .flash").forEach(function (flash) { flash.remove(); });
      resetBin(); refreshDay();
    } catch (error) {
      status.textContent = error.message;
    } finally {
      busy = false;
      form.removeAttribute("aria-busy");
      controls.forEach(function (control) { control.disabled = false; });
      if (form.classList.contains("add-task")) form.querySelector('[name="task[description]"]').focus();
    }
  }
  document.querySelectorAll(".add-task, .inline-edit").forEach(updatePickers);
  document.addEventListener("submit", function (event) {
    var form = event.target;
    if (!form.matches(".add-task, .inline-edit, .star-form, .bin-form")) return;
    event.preventDefault();
    if (form.matches(".bin-form")) {
      var bin = form.querySelector(".bin");
      if (armedBin !== bin) {
        resetBin(); armedBin = bin;
        bin.dataset.originalLabel = bin.getAttribute("aria-label");
        bin.classList.add("armed");
        bin.setAttribute("aria-label", "Confirm deletion. " + bin.dataset.originalLabel);
        binTimer = setTimeout(resetBin, 5000); return;
      }
    }
    submitTaskForm(form);
  });
  document.addEventListener("click", function (event) {
    var row = event.target.closest(".task-row");
    document.querySelectorAll(".task-row.active").forEach(function (other) { if (other !== row) other.classList.remove("active"); });
    if (row) row.classList.add("active");
    if (event.target.closest(".task-name, .task-date")) startEditing(row, !!event.target.closest(".task-date"));
    var clear = event.target.closest(".clear-date");
    if (clear) {
      var input = document.getElementById(clear.dataset.dateInput);
      input.value = ""; input.dispatchEvent(new Event("change", { bubbles: true }));
    }
  });
  document.addEventListener("pointerdown", function (event) {
    if (armedBin && !armedBin.contains(event.target)) resetBin();
    document.querySelectorAll(".task-row.editing").forEach(function (row) {
      if (!row.contains(event.target)) submitTaskForm(row.querySelector(".inline-edit"));
    });
    document.querySelectorAll(".date-picker[open], .account-menu[open]").forEach(function (menu) {
      if (!menu.contains(event.target)) menu.open = false;
    });
  });
  document.addEventListener("focusin", function (event) {
    if (armedBin && !armedBin.contains(event.target)) resetBin();
  });
  document.addEventListener("focusout", function (event) {
    var row = event.target.closest(".task-row.editing");
    if (row) setTimeout(function () {
      if (row.isConnected && row.classList.contains("editing") && !row.contains(document.activeElement)) submitTaskForm(row.querySelector(".inline-edit"));
    }, 0);
  });
  document.addEventListener("keydown", function (event) {
    if (event.key === "Escape") {
      resetBin();
      var row = event.target.closest(".task-row.editing");
      if (row && !busy) { finishEditing(row.querySelector(".inline-edit"), true); row.querySelector(".task-name").focus(); }
      document.querySelectorAll(".date-picker[open], .account-menu[open]").forEach(function (menu) { menu.open = false; menu.querySelector("summary").focus(); });
    }
    if (event.key === "Enter" && !event.isComposing && event.target.matches('.inline-edit textarea, #new-task-description')) {
      event.preventDefault(); event.target.form.requestSubmit();
    }
  });
  document.addEventListener("input", function (event) {
    if (event.target.matches(".inline-edit textarea")) sizeEditor(event.target);
  });
  document.addEventListener("change", function (event) {
    if (event.target.closest(".tag-picker, .date-picker")) updatePickers(event.target.form);
  });
  document.querySelectorAll(".avatar").forEach(function (img) {
    img.addEventListener("error", function () { img.hidden = true; });
  });
  var detected = Intl.DateTimeFormat().resolvedOptions().timeZone;
  var detectForm = document.getElementById("detect-timezone");
  if (detectForm && detected) {
    detectForm.querySelector("input[name='user[time_zone]']").value = detected;
    fetch(detectForm.action, { method: "PATCH", headers: { "X-CSRF-Token": csrf.content, "Accept": "application/json" }, body: new FormData(detectForm) })
      .then(function (response) { if (!response.ok) throw new Error("Timezone could not be saved"); return response.json(); })
      .then(function (profile) {
        workspace.dataset.timeZone = profile.time_zone;
        midnight = Date.parse(profile.reset_at);
        detectForm.remove();
        document.dispatchEvent(new Event("visibilitychange"));
      })
      .catch(function () { /* The profile link remains available if detection cannot be saved. */ });
  }
  var suggestForm = document.getElementById("suggest-timezone");
  if (suggestForm && detected) suggestForm.querySelector("select").value = detected;

  if (workspace) {
    var midnight = Date.parse(workspace.dataset.resetAt);
    refreshDay = function () {
      var now = Date.now();
      document.querySelectorAll("[data-starred-until]").forEach(function (row) {
        var expires = Date.parse(row.dataset.starredUntil);
        if (!expires || now < expires) return;
        var button = row.querySelector(".star");
        button.textContent = "☆";
        button.setAttribute("aria-pressed", "false");
        button.setAttribute("aria-label", "Star " + row.querySelector(".task-name").textContent);
        row.querySelector('input[name="starred"]').value = "true";
        row.removeAttribute("data-starred-until");
        if (new URLSearchParams(window.location.search).get("view") === "starred") {
          if (row.classList.contains("editing")) row.dataset.expired = "true";
          else row.remove();
        }
      });
      // Update dates without reloading forms or losing an unfinished edit.
      var parts = new Intl.DateTimeFormat("en", { timeZone: workspace.dataset.timeZone, year: "numeric", month: "2-digit", day: "2-digit" }).formatToParts(now);
      var date = {};
      parts.forEach(function (part) { date[part.type] = part.value; });
      var today = date.year + "-" + date.month + "-" + date.day;
      if (today !== workspace.dataset.today || now >= midnight) {
        workspace.dataset.today = today;
        workspace.querySelector(".page-heading > .muted").textContent = new Intl.DateTimeFormat("en-GB", { timeZone: workspace.dataset.timeZone, weekday: "long", day: "numeric", month: "long" }).format(now);
        document.querySelectorAll(".task-date").forEach(function (button) {
          var due = button.dataset.dueDate;
          button.querySelector(".urgency").textContent = due < today ? "‼️" : due === today ? "❗" : "";
          button.querySelector(".date-label").textContent = relativeDate(due, today);
        });
        document.querySelectorAll(".add-task").forEach(updatePickers);
      }
      document.querySelectorAll(".tag-section").forEach(function (section) {
        var count = section.querySelectorAll(".task-row").length;
        if (!count) section.remove();
        else section.querySelector(".count").textContent = count;
      });
      if (new URLSearchParams(window.location.search).get("view") === "starred" && !document.querySelector(".task-row") && !document.querySelector("#task-groups .empty")) {
        var empty = document.createElement("p"); empty.className = "empty";
        empty.textContent = "No starred tasks. Choose a star in All tasks to focus on it today.";
        document.getElementById("task-groups").appendChild(empty);
      }
    };
    setInterval(refreshDay, 1000);
    document.addEventListener("visibilitychange", refreshDay);
    window.addEventListener("pageshow", refreshDay);
  }

  var list = document.getElementById("profile-tags"), dragged, saving = false;
  if (list) {
    function saveOrder(previous) {
      saving = true;
      var ids = Array.from(list.children).map(function (row) { return row.dataset.tagId; });
      fetch(list.dataset.reorderUrl, { method: "PATCH", headers: { "Content-Type": "application/json", "X-CSRF-Token": csrf.content }, body: JSON.stringify({ tag_ids: ids }) })
        .then(function (response) { if (!response.ok) throw new Error("Unable to save order"); document.getElementById("reorder-status").textContent = "Tag order saved."; })
        .catch(function () { previous.forEach(function (row) { list.appendChild(row); }); document.getElementById("reorder-status").textContent = "Could not save tag order. Please try again."; })
        .finally(function () { saving = false; });
    }
    list.addEventListener("click", function (event) {
      var button = event.target.closest("[data-move]"); if (!button || saving) return;
      var previous = Array.from(list.children), row = button.closest("li");
      if (button.dataset.move === "up" && row.previousElementSibling) list.insertBefore(row, row.previousElementSibling);
      else if (button.dataset.move === "down" && row.nextElementSibling) list.insertBefore(row.nextElementSibling, row);
      else return;
      saveOrder(previous);
    });
    list.addEventListener("dragstart", function (event) {
      if (saving || event.target.closest("input, select, button")) { event.preventDefault(); return; }
      dragged = event.target.closest("li"); event.dataTransfer.setData("text/plain", dragged.dataset.tagId);
    });
    list.addEventListener("dragover", function (event) { event.preventDefault(); });
    list.addEventListener("drop", function (event) {
      event.preventDefault(); var target = event.target.closest("li");
      if (!dragged || !target || target === dragged || saving) return;
      var previous = Array.from(list.children);
      var after = event.clientY > target.getBoundingClientRect().top + target.offsetHeight / 2;
      list.insertBefore(dragged, after ? target.nextSibling : target); dragged = null;
      saveOrder(previous);
    });
    list.addEventListener("dragend", function () { dragged = null; });
  }
});
