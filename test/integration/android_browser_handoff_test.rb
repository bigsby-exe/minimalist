require "test_helper"

class AndroidBrowserHandoffTest < ActionDispatch::IntegrationTest
  include Devise::Test::IntegrationHelpers

  setup do
    @token = SecureRandom.hex(32)
    @device = users(:one).android_devices.create!(name: "Phone", token_digest: AndroidDevice.digest(@token))
    @headers = { "Authorization" => "Bearer #{@token}" }
  end

  def issue(purpose: "open", destination: "/dashboard")
    post mobile_browser_handoff_path, params: { purpose: purpose, destination: destination }, headers: @headers, as: :json
    assert_response :created
    response.parsed_body.fetch("path")
  end

  test "opening the app requires its device and a matching Google browser session" do
    post mobile_browser_handoff_path, as: :json
    assert_response :unauthorized
    path = issue(destination: "/dashboard?view=starred#task-123")
    get path
    assert_redirected_to new_user_session_path
    assert_equal 1, AndroidBrowserHandoff.count
    sign_in users(:two)
    get path
    assert_response :conflict
    assert_equal 1, AndroidBrowserHandoff.count
    sign_out users(:two)
    sign_in users(:one)
    get path
    assert_redirected_to "/dashboard?view=starred#task-123"
    follow_redirect!
    assert_select "a", text: "Phone and widgets"
    assert_equal 0, AndroidBrowserHandoff.count
    get path
    assert_response :gone
  end

  test "expired or revoked handoffs cannot bind a browser" do
    path = issue
    sign_in users(:one)
    travel 6.minutes do
      get path
      assert_response :gone
    end
    path = issue
    @device.destroy!
    get path
    assert_response :gone
  end

  test "app signout revokes only this phone and ends its browser session" do
    other = users(:one).android_devices.create!(name: "Other phone", token_digest: AndroidDevice.digest(SecureRandom.hex(32)))
    sign_in users(:one)
    path = issue(purpose: "sign_out")
    assert_not AndroidDevice.exists?(@device.id)
    assert AndroidDevice.exists?(other.id)
    get mobile_workspace_path, headers: @headers, as: :json
    assert_response :unauthorized
    get path
    assert_redirected_to root_path
    get dashboard_path
    assert_redirected_to new_user_session_path
    sign_in users(:one)
    get path
    assert_response :gone
    get dashboard_path
    assert_response :success
  end

  test "desktop signout leaves phones connected" do
    sign_in users(:one)
    delete destroy_user_session_path
    assert AndroidDevice.exists?(@device.id)
  end

  test "handoffs cannot redirect to another site or arbitrary path" do
    %w[https://example.com //example.com /profile].each do |destination|
      post mobile_browser_handoff_path, params: { destination: destination }, headers: @headers, as: :json
      assert_response :unprocessable_entity
    end
  end

  test "signout handoff does not sign out a different browser account" do
    path = issue(purpose: "sign_out")
    sign_in users(:two)
    get path
    assert_response :conflict
    get dashboard_path
    assert_response :success
  end
end
