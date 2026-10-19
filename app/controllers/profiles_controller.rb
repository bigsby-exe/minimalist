class ProfilesController < ApplicationController
  before_action :authenticate_user!

  def show
    @tags = current_user.tags.in_profile_order
  end

  def update
    if current_user.update(params.require(:user).permit(:time_zone))
      # A changed timezone applies to the next reset for currently starred tasks.
      current_user.tasks.starred.update_all(starred_until: current_user.next_midnight)
      respond_to do |format|
        format.html { redirect_to profile_path, notice: "Timezone saved." }
        format.json { render json: { time_zone: current_user.time_zone, reset_at: current_user.next_midnight.iso8601 } }
      end
    else
      respond_to do |format|
        format.html { redirect_to profile_path, alert: current_user.errors.full_messages.to_sentence }
        format.json { render json: { errors: current_user.errors.full_messages }, status: :unprocessable_entity }
      end
    end
  end
end
