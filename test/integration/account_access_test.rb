require "test_helper"

class AccountAccessTest < ActionDispatch::IntegrationTest
  include Devise::Test::IntegrationHelpers

  setup do
    @allowed_emails = ENV["MINIMALIST_ALLOWED_EMAILS"]
    @oauth_test_mode = OmniAuth.config.test_mode
    @oauth_mock = OmniAuth.config.mock_auth[:google_oauth2]
    OmniAuth.config.test_mode = true
  end

  teardown do
    ENV["MINIMALIST_ALLOWED_EMAILS"] = @allowed_emails
    OmniAuth.config.test_mode = @oauth_test_mode
    OmniAuth.config.mock_auth[:google_oauth2] = @oauth_mock
  end

  test "approved Google login creates an account and continues phone approval" do
    ENV["MINIMALIST_ALLOWED_EMAILS"] = " FIRST@example.com, new@example.com "
    authorization, = DeviceAuthorization.issue!("New phone")
    get connect_device_path(code: authorization.user_code)
    mock_google("new@example.com")
    assert_difference "User.count", 1 do
      get user_google_oauth2_omniauth_callback_path
    end
    assert_redirected_to connect_device_path(code: authorization.user_code)
    follow_redirect!
    assert_response :success
    assert User.allowed_email?("First@EXAMPLE.com")
  end

  test "Google login resumes an app handoff without granting another account access" do
    device = users(:one).android_devices.create!(name: "Phone", token_digest: AndroidDevice.digest(SecureRandom.hex(32)))
    token = AndroidBrowserHandoff.issue!(device, purpose: "open")
    get phone_launch_path(token: token)
    assert_redirected_to new_user_session_path
    mock_google(users(:one).email)
    get user_google_oauth2_omniauth_callback_path
    assert_redirected_to phone_launch_path(token: token)
    follow_redirect!
    assert_redirected_to dashboard_path
    follow_redirect!
    assert_select "a", text: "Phone and widgets"
  end

  test "unapproved or unverified Google emails create no accounts or identities" do
    ["stranger@example.com", nil].each do |email|
      mock_google(email)
      assert_no_difference ["User.count", "Identity.count"] do
        get user_google_oauth2_omniauth_callback_path
      end
      assert_redirected_to new_user_session_path
      follow_redirect!
      assert_response :success
      get dashboard_path
      assert_redirected_to new_user_session_path
    end
  end

  test "empty allowlist denies even an existing Google account" do
    ENV["MINIMALIST_ALLOWED_EMAILS"] = ""
    mock_google(users(:one).email)
    get user_google_oauth2_omniauth_callback_path
    assert_redirected_to new_user_session_path
  end

  test "removing an account blocks existing browser and phone access" do
    sign_in users(:one)
    token = SecureRandom.hex(32)
    users(:one).android_devices.create!(name: "Phone", token_digest: AndroidDevice.digest(token))
    headers = { "Authorization" => "Bearer #{token}" }
    get dashboard_path
    assert_response :success
    get mobile_workspace_path, headers: headers, as: :json
    assert_response :success

    ENV["MINIMALIST_ALLOWED_EMAILS"] = users(:two).email
    assert_no_difference "Task.count" do
      post mobile_tasks_path, headers: headers, as: :json, params: {
        request_id: SecureRandom.uuid, task: { description: "Denied", tag_id: tags(:household).id }
      }
      assert_response :unauthorized
    end
    get mobile_workspace_path, headers: headers, as: :json
    assert_response :unauthorized
    get mobile_authorization_path, headers: headers, as: :json
    assert_response :gone
    get dashboard_path
    assert_redirected_to new_user_session_path
    follow_redirect!
    assert_response :success
  end

  private

  def mock_google(email)
    OmniAuth.config.mock_auth[:google_oauth2] = OmniAuth::AuthHash.new(
      provider: "google_oauth2", uid: "access-test", info: { email: email, name: "Test account" }
    )
  end
end
