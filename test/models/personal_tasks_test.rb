require "test_helper"

class PersonalTasksTest < ActiveSupport::TestCase
  test "new accounts have the agreed ordered tags" do
    user = User.create!(email: "new@example.com")
    assert_equal ["Household", "Medical", "Work"], user.tags.in_profile_order.pluck(:name)
    assert_equal ["🏠", "🩺", "💼"], user.tags.in_profile_order.pluck(:emoji)
  end

  test "a task requires text and its owner's tag but not a date" do
    task = tasks(:one)
    assert task.valid?
    task.tag = tags(:private)
    assert_not task.valid?
    task.tag = nil
    assert_not task.valid?
    task.tag = tags(:household)
    task.description = " "
    assert_not task.valid?
  end

  test "dates sort earliest first with undated last and creation order for ties" do
    user = users(:one)
    late = user.tasks.create!(tag: tags(:household), description: "Late", due_date: "2026-10-20")
    early = user.tasks.create!(tag: tags(:household), description: "Early", due_date: "2026-10-18", created_at: 2.days.ago)
    tie = user.tasks.create!(tag: tags(:household), description: "Tie", due_date: "2026-10-18")
    assert_equal [early, tie, late, tasks(:one)], user.tasks.in_date_order.to_a
  end

  test "urgency distinguishes today and overdue without marking undated or future tasks" do
    task = tasks(:one)
    today = Date.new(2026, 10, 18)
    { nil => "", today => "❗", today - 1 => "‼️", today + 1 => "" }.each do |date, marker|
      task.due_date = date
      assert_equal marker, task.urgency(today)
    end
  end

  test "stars expire exactly at profile midnight while tasks remain" do
    travel_to Time.utc(2026, 10, 18, 22, 59, 59) do
      user = users(:one)
      task = tasks(:one)
      task.update!(starred_until: user.next_midnight)
      assert_equal Time.utc(2026, 10, 18, 23), task.starred_until
      assert task.starred?
      travel 1.second
      assert_not task.reload.starred?
      assert_empty user.tasks.starred
      assert user.tasks.exists?(task.id)
    end
  end

  test "midnight uses the calendar day over daylight saving transitions" do
    travel_to Time.utc(2026, 10, 24, 23, 30) do
      assert_equal Time.utc(2026, 10, 26, 0), users(:one).next_midnight
    end
    travel_to Time.utc(2026, 3, 29, 0, 30) do
      assert_equal Time.utc(2026, 3, 29, 23), users(:one).next_midnight
    end
  end

  test "occupied tags cannot be deleted directly and invalid colours are rejected" do
    assert_not tags(:household).destroy
    tag = tags(:work)
    tag.color = "red; display:none"
    assert_not tag.valid?
  end
end
