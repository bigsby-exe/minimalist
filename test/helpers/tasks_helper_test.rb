require "test_helper"

class TasksHelperTest < ActionView::TestCase
  include TasksHelper

  test "relative dates use calendar days and switch to absolute dates beyond a week" do
    today = Date.new(2026, 12, 29)
    { 0 => "Today", 1 => "Tomorrow", -1 => "Yesterday", 7 => "In 7 days",
      -7 => "7 days overdue", 8 => "6 Jan 2027", -8 => "21 Dec" }.each do |offset, label|
      assert_equal label, task_date_label(today + offset, today)
    end
    assert_nil task_date_label(nil, today)
  end
end
