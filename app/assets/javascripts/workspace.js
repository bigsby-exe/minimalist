document.addEventListener("DOMContentLoaded", function () {
  "use strict";
  var csrf = document.querySelector('meta[name="csrf-token"]');
  document.querySelectorAll(".bin-form").forEach(function (form) {
    form.addEventListener("submit", function (event) {
      if (!window.confirm("Bin this task?")) event.preventDefault();
    });
  });
  document.querySelectorAll(".clear-date").forEach(function (button) {
    button.addEventListener("click", function () { document.getElementById(button.dataset.dateInput).value = ""; });
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

  var workspace = document.getElementById("task-workspace");
  if (workspace) {
    var midnight = Date.parse(workspace.dataset.resetAt);
    function refreshDay() {
      var now = Date.now();
      document.querySelectorAll("[data-starred-until]").forEach(function (row) {
        var expires = Date.parse(row.dataset.starredUntil);
        if (!expires || now < expires) return;
        var button = row.querySelector(".star");
        button.textContent = "☆";
        button.setAttribute("aria-pressed", "false");
        button.setAttribute("aria-label", "Star " + row.querySelector(".task-copy p").textContent);
        row.querySelector('input[name="starred"]').value = "true";
        row.removeAttribute("data-starred-until");
        if (new URLSearchParams(window.location.search).get("view") === "starred") {
          row.remove();
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
        document.querySelectorAll(".urgency").forEach(function (marker) {
          var due = marker.dataset.dueDate;
          marker.textContent = due && due < today ? "‼️" : due === today ? "❗" : "";
        });
        document.querySelectorAll(".tag-section").forEach(function (section) { if (!section.querySelector(".task-row")) section.remove(); });
        if (new URLSearchParams(window.location.search).get("view") === "starred" && !document.querySelector(".task-row") && !document.querySelector("#task-groups .empty")) {
          var empty = document.createElement("p"); empty.className = "empty";
          empty.textContent = "No starred tasks. Choose a star in All tasks to focus on it today.";
          document.getElementById("task-groups").appendChild(empty);
        }
      }
    }
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
