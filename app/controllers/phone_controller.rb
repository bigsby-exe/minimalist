class PhoneController < ApplicationController
  before_action do
    response.headers["Cache-Control"] = "no-store"
    response.headers["Referrer-Policy"] = "no-referrer"
  end

  def launch
    handoff = AndroidBrowserHandoff.pending(params[:token])
    return render plain: "This connection has expired. Reopen Steps to try again.", status: :gone unless handoff
    if handoff.purpose == "open" && !current_user
      session[:phone_handoff_token] = params[:token]
      return redirect_to new_user_session_path
    end
    handoff.with_lock do
      return head :gone if handoff.expires_at <= Time.current
      if current_user && current_user.id != handoff.user_id
        return render :account_mismatch, status: :conflict
      end
      return head :forbidden unless handoff.user.access_allowed?
      if handoff.purpose == "sign_out"
        sign_out current_user if current_user
        reset_session
        handoff.destroy!
        return redirect_to root_path
      end
      device = current_user.android_devices.find_by(id: handoff.android_device_id)
      return head :gone unless device
      session[:phone_device_id] = device.id
      destination = handoff.destination
      handoff.destroy!
      redirect_to destination
    end
  rescue ActiveRecord::RecordNotFound
    head :gone
  end
end
