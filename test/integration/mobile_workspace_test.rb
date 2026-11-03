require "test_helper"

class MobileWorkspaceTest < ActionDispatch::IntegrationTest
  include Devise::Test::IntegrationHelpers

  setup do
    @token = SecureRandom.hex(32)
    @device = users(:one).android_devices.create!(name: "Test phone", token_digest: AndroidDevice.digest(@token))
    @headers = { "Authorization" => "Bearer #{@token}" }
  end

  test "device API rejects browser sessions and old user API tokens" do
    sign_in users(:one)
    get mobile_workspace_path, as: :json
    assert_response :unauthorized
    get mobile_workspace_path, headers: { "Authorization" => "Bearer #{users(:one).api_token}" }, as: :json
    assert_response :unauthorized
    get mobile_workspace_path, headers: @headers, as: :json
    assert_response :success
    assert_equal "no-store", response.headers["Cache-Control"]
  end

  test "pairing requires explicit browser approval and expires" do
    post mobile_authorization_path, params: { name: "My phone" }, as: :json
    assert_response :created
    connection = response.parsed_body
    headers = { "Authorization" => "Bearer #{connection.fetch('device_token')}" }
    get mobile_authorization_path, headers: headers, as: :json
    assert_response :accepted
    get mobile_workspace_path, headers: headers, as: :json
    assert_response :unauthorized
    get connection.fetch("verification_path")
    assert_redirected_to new_user_session_path
    sign_in users(:one)
    get connection.fetch("verification_path")
    assert_response :success
    assert_select "strong", text: connection.fetch("user_code")
    assert_difference "AndroidDevice.count", 1 do
      post approve_device_path, params: { code: connection.fetch("user_code") }
    end
    get mobile_authorization_path, headers: headers, as: :json
    assert_equal "authorized", response.parsed_body["status"]
    get mobile_workspace_path, headers: headers, as: :json
    assert_response :success
    assert_no_difference "AndroidDevice.count" do
      post approve_device_path, params: { code: connection.fetch("user_code") }
    end
    authorization, expired_token = DeviceAuthorization.issue!("Expired")
    travel 11.minutes do
      post approve_device_path, params: { code: authorization.user_code }
      get mobile_authorization_path, headers: { "Authorization" => "Bearer #{expired_token}" }, as: :json
      assert_response :gone
    end
  end

  test "workspace filters ownership and expiry and respects profile ordering" do
    travel_to Time.utc(2026, 10, 24, 12) do
      users(:one).update!(time_zone: "Europe/London")
      first = tasks(:one)
      first.update!(starred_until: users(:one).next_midnight)
      later = users(:one).tasks.create!(tag: tags(:work), description: "Work", due_date: Date.yesterday, starred_until: users(:one).next_midnight)
      users(:one).tasks.create!(tag: tags(:household), description: "Expired", starred_until: Time.current)
      tasks(:two).update!(starred_until: 1.day.from_now)
      get mobile_workspace_path, headers: @headers, as: :json
      assert_equal [first.id, later.id], response.parsed_body["tasks"].map { |task| task["id"] }
      assert_equal "Europe/London", response.parsed_body["time_zone"]
      travel_to users(:one).next_midnight
      get mobile_workspace_path, headers: @headers, as: :json
      assert_empty response.parsed_body["tasks"]
    end
  end

  test "task retries are idempotent even after binning and reject changed payloads" do
    payload = { request_id: SecureRandom.uuid, task: { description: "🧹 Clean", tag_id: tags(:household).id }, starred: true }
    assert_difference "Task.count", 1 do
      2.times { post mobile_tasks_path, params: payload, headers: @headers, as: :json }
    end
    task = Task.find(response.parsed_body.fetch("id"))
    assert task.starred?
    post mobile_tasks_path, params: payload.deep_merge(task: { description: "Changed" }), headers: @headers, as: :json
    assert_response :conflict
    task.destroy!
    assert_no_difference "Task.count" do
      post mobile_tasks_path, params: payload, headers: @headers, as: :json
    end
    assert_response :gone
  end

  test "foreign tags blank text and invalid dates cannot be submitted" do
    [{ description: "Bad tag", tag_id: tags(:private).id },
     { description: " ", tag_id: tags(:household).id },
     { description: "Bad date", tag_id: tags(:household).id, due_date: "invalid" }].each do |attributes|
      assert_no_difference "Task.count" do
        post mobile_tasks_path, params: { request_id: SecureRandom.uuid, task: attributes }, headers: @headers, as: :json
      end
      assert_response :unprocessable_entity
    end
  end

  test "unstar is repeatable and cannot affect another account" do
    tasks(:one).update!(starred_until: 1.day.from_now)
    2.times do
      delete star_mobile_task_path(tasks(:one)), headers: @headers, as: :json
      assert_response :no_content
    end
    assert_not tasks(:one).reload.starred?
    delete star_mobile_task_path(tasks(:two)), headers: @headers, as: :json
    assert_response :not_found
  end

  test "revocation is scoped to the owner and immediately stops API access" do
    sign_in users(:two)
    delete device_path(@device)
    assert AndroidDevice.exists?(@device.id)
    sign_in users(:one)
    delete device_path(@device)
    assert_not AndroidDevice.exists?(@device.id)
    get mobile_workspace_path, headers: @headers, as: :json
    assert_response :unauthorized
  end

  test "phone can revoke its own credential" do
    delete mobile_device_path, headers: @headers, as: :json
    assert_response :no_content
    get mobile_workspace_path, headers: @headers, as: :json
    assert_response :unauthorized
  end

  test "approval detects an unset timezone without overwriting an existing profile" do
    sign_in users(:one)
    users(:one).update!(time_zone: nil)
    authorization, = DeviceAuthorization.issue!("First phone")
    post approve_device_path, params: { code: authorization.user_code, time_zone: "Europe/London" }
    assert_equal "Europe/London", users(:one).reload.time_zone
    authorization, = DeviceAuthorization.issue!("Travelling phone")
    post approve_device_path, params: { code: authorization.user_code, time_zone: "Asia/Tokyo" }
    assert_equal "Europe/London", users(:one).reload.time_zone
  end
end
