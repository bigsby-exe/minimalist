require "test_helper"

class BrowsingTest < ActionDispatch::IntegrationTest
  test "landing page offers sign in for private tasks" do
    get "/"
    assert_response :success
    assert_select "form[action='#{user_google_oauth2_omniauth_authorize_path}']"
    assert_select "a[href='/s/new']", count: 0
  end
end
