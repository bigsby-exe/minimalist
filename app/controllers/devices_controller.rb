class DevicesController < ApplicationController
  before_action :remember_connection, only: :connect
  before_action :authenticate_user!
  before_action do
    response.headers["Cache-Control"] = "no-store"
    response.headers["Referrer-Policy"] = "no-referrer"
  end

  def connect
    @authorization = DeviceAuthorization.where("expires_at > ?", Time.current).find_by!(user_code: params[:code].to_s)
  end

  def approve
    authorization = DeviceAuthorization.find_by!(user_code: params[:code].to_s)
    current_user.with_lock do
      zone = params[:time_zone].to_s
      current_user.update!(time_zone: zone) if current_user.time_zone.nil? && TZInfo::Timezone.all_identifiers.include?(zone)
      authorization.approve!(current_user)
    end
    redirect_to profile_path, notice: "Phone connected. Return to Steps on your phone."
  end

  def destroy
    current_user.android_devices.find(params[:id]).destroy!
    redirect_to profile_path, notice: "Phone disconnected."
  end

  private

  def remember_connection
    session.delete(:phone_handoff_token)
    code = params[:code].to_s
    session[:device_authorization_code] = code if !current_user && code.match?(/\A[0-9A-F]{12}\z/)
  end
end
