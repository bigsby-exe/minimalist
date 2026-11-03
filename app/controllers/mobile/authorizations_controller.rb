module Mobile
  class AuthorizationsController < BaseController
    skip_before_action :authenticate_device!
    RATE_LIMIT_STORE = ActiveSupport::Cache::MemoryStore.new(size: 2.megabytes)
    rate_limit to: 10, within: 1.minute, only: :create, store: RATE_LIMIT_STORE,
      with: -> { render json: { error: "Too many connection attempts. Wait a minute." }, status: :too_many_requests }
    rate_limit to: 30, within: 1.minute, only: :show, store: RATE_LIMIT_STORE,
      with: -> { render json: { error: "Wait a minute before checking the connection again." }, status: :too_many_requests }

    def create
      DeviceAuthorization.where("expires_at <= ?", Time.current).delete_all
      authorization, token = DeviceAuthorization.issue!(params.require(:name).to_s.strip)
      render json: { device_token: token, user_code: authorization.user_code,
        verification_path: connect_device_path(code: authorization.user_code),
        expires_at: authorization.expires_at.iso8601, interval: 5 }, status: :created
    end

    def show
      if device = AndroidDevice.for_token(bearer_token)
        render json: { status: "authorized", email: device.user.email }
      elsif bearer_token.present? && DeviceAuthorization.where(token_digest: AndroidDevice.digest(bearer_token)).where("expires_at > ?", Time.current).exists?
        render json: { status: "pending" }, status: :accepted
      else
        render json: { error: "Connection expired. Start again." }, status: :gone
      end
    end
  end
end
