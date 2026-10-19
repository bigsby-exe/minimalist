class TagsController < ApplicationController
  before_action :authenticate_user!

  def create
    tag = current_user.tags.new(tag_params)
    tag.position = (current_user.tags.maximum(:position) || -1) + 1
    tag.save ? redirect_to(profile_path) : redirect_to(profile_path, alert: tag.errors.full_messages.to_sentence)
  end

  def update
    tag = current_user.tags.find(params[:id])
    tag.update(tag_params) ? redirect_to(profile_path) : redirect_to(profile_path, alert: tag.errors.full_messages.to_sentence)
  end

  def reorder
    ids = Array(params[:tag_ids]).map(&:to_s)
    current_user.with_lock do
      tags = current_user.tags.to_a
      unless ids.sort == tags.map { |tag| tag.id.to_s }.sort
        return head :unprocessable_entity
      end
      ids.each_with_index { |id, index| tags.find { |tag| tag.id.to_s == id }.update!(position: index) }
    end
    head :no_content
  end

  def destroy
    tag = current_user.tags.find(params[:id])
    current_user.with_lock do
      if tag.tasks.exists?
        replacement = current_user.tags.where.not(id: tag.id).find_by(id: params[:replacement_id])
        unless replacement
          return redirect_to profile_path, alert: "Choose another tag for these tasks before deleting this tag."
        end
        tag.tasks.update_all(tag_id: replacement.id)
      end
      tag.destroy!
    end
    redirect_to profile_path
  end

  private

  def tag_params
    params.require(:tag).permit(:name, :emoji, :color)
  end
end
