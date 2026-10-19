require "test_helper"

class TaskWorkspaceTest < ActionDispatch::IntegrationTest
  include Devise::Test::IntegrationHelpers

  setup { sign_in users(:one) }

  test "dashboard is private and exposes calendar fields without completion controls" do
    get dashboard_path
    assert_response :success
    assert_select "input[type=date]"
    assert_select "input[type=checkbox]", count: 0
    assert_includes response.body, "Clean the kitchen"
    assert_not_includes response.body, "Private appointment"
    sign_out users(:one)
    get dashboard_path
    assert_redirected_to new_user_session_path
  end

  test "creating remembers the tag and accepts no date" do
    assert_difference "users(:one).tasks.count", 1 do
      post tasks_path, params: { task: { description: "New task", tag_id: tags(:work).id } }
    end
    follow_redirect!
    assert_select "#new-task-tag option[selected][value='#{tags(:work).id}']"
  end

  test "cannot assign another user's tag or edit star or bin their tasks" do
    assert_no_difference "Task.count" do
      post tasks_path, params: { task: { description: "Intruder", tag_id: tags(:private).id } }
    end
    other = tasks(:two)
    patch task_path(other), params: { task: { description: "Changed" } }
    assert_equal "Private appointment", other.reload.description
    patch star_task_path(other), params: { starred: "true" }
    assert_not other.reload.starred?
    assert_no_difference "Task.count" do
      delete task_path(other)
    end
  end

  test "blank edits preserve the task and dates can be cleared" do
    task = tasks(:one)
    patch task_path(task), params: { task: { description: " " } }
    assert_equal "Clean the kitchen", task.reload.description
    patch task_path(task), params: { task: { due_date: "2026-10-18" } }
    assert_equal Date.new(2026, 10, 18), task.reload.due_date
    patch task_path(task), params: { task: { due_date: "" } }
    assert_nil task.reload.due_date
  end

  test "starring is an idempotent view change and binning removes the underlying task" do
    task = tasks(:one)
    2.times { patch star_task_path(task), params: { starred: "true" } }
    assert task.reload.starred?
    get dashboard_path(view: "starred")
    assert_includes response.body, task.description
    patch star_task_path(task), params: { starred: "false", view: "starred" }
    follow_redirect!
    assert_not_includes response.body, task.description
    assert Task.exists?(task.id)
    assert_difference "Task.count", -1 do
      delete task_path(task)
    end
  end

  test "profile changes persist and tag order rejects foreign or incomplete ids" do
    get profile_path
    assert_response :success
    patch profile_path, params: { user: { time_zone: "America/New_York" } }
    assert_equal "America/New_York", users(:one).reload.time_zone
    patch reorder_tags_path, params: { tag_ids: [tags(:work).id, tags(:household).id] }, as: :json
    assert_response :no_content
    assert_equal ["Work", "Household"], users(:one).tags.in_profile_order.pluck(:name)
    patch reorder_tags_path, params: { tag_ids: [tags(:private).id] }, as: :json
    assert_response :unprocessable_entity
  end

  test "deleting an occupied tag requires an owned replacement" do
    tag = tags(:household)
    delete tag_path(tag)
    assert Tag.exists?(tag.id)
    delete tag_path(tag), params: { replacement_id: tags(:private).id }
    assert Tag.exists?(tag.id)
    delete tag_path(tag), params: { replacement_id: tags(:work).id }
    assert_not Tag.exists?(tag.id)
    assert_equal tags(:work).id, tasks(:one).reload.tag_id
  end

  test "tag editing updates existing task colours and names" do
    patch tag_path(tags(:household)), params: { tag: { name: "Home", emoji: "🏡", color: "#fff0c7" } }
    get dashboard_path
    assert_includes response.body, "🏡 Home"
    assert_includes response.body, "--tag-color: #fff0c7"
  end

  test "anonymous and shared list routes are retired" do
    assert_raises(ActionController::RoutingError) { Rails.application.routes.recognize_path("/s/new") }
    assert_raises(ActionController::RoutingError) { Rails.application.routes.recognize_path("/api/lists") }
  end
  test "invalid new tasks keep entered text for correction" do
    post tasks_path, params: { task: { description: "Keep my draft", due_date: "not-a-date" } }
    assert_response :unprocessable_entity
    assert_select "#new-task-description[value='Keep my draft']"
  end

  test "browser timezone detection saves without a page reload" do
    patch profile_path, params: { user: { time_zone: "Europe/London" } }, as: :json
    assert_response :success
    assert_equal "Europe/London", response.parsed_body["time_zone"]
    assert Time.iso8601(response.parsed_body["reset_at"]) > Time.current
    patch profile_path, params: { user: { time_zone: "not-a-timezone" } }, as: :json
    assert_response :unprocessable_entity
    assert_equal "Europe/London", users(:one).reload.time_zone
  end

end
