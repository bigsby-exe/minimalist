module Mobile
  class BrowserHandoffsController < BaseController
    def create
      purpose = params[:purpose] == "sign_out" ? "sign_out" : "open"
      token = nil
      @device.with_lock do
        token = AndroidBrowserHandoff.issue!(@device, purpose: purpose,
          destination: params[:destination].presence || "/dashboard")
        @device.destroy! if purpose == "sign_out"
      end
      render json: { path: phone_launch_path(token: token) }, status: :created
    end
  end
end
