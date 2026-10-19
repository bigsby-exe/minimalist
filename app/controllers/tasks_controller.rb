class TasksController < ApplicationController
  before_action :authenticate_user!

  def index
    load_dashboard
  end

  def create
    task = current_user.tasks.new(task_params)
    if task.save
      session[:last_tag_id] = task.tag_id
      redirect_to dashboard_path(view: params[:view]), notice: "Task added."
    else
      load_dashboard(task)
      flash.now[:alert] = task.errors.full_messages.to_sentence
      render :index, status: :unprocessable_entity
    end
  end

  def update
    task = current_user.tasks.find(params[:id])
    if task.update(task_params)
      redirect_to dashboard_path(view: params[:view])
    else
      redirect_to dashboard_path(view: params[:view]), alert: task.errors.full_messages.to_sentence
    end
  end

  def star
    task = current_user.tasks.find(params[:id])
    # An explicit desired state avoids toggling twice after a retry.
    task.update!(starred_until: params[:starred] == "true" ? current_user.next_midnight : nil)
    redirect_to dashboard_path(view: params[:view])
  end

  def destroy
    current_user.tasks.find(params[:id]).destroy!
    redirect_to dashboard_path(view: params[:view]), notice: "Task binned."
  end

  private

  def load_dashboard(task = nil)
    @starred = params[:view] == "starred"
    @tags = current_user.tags.in_profile_order
    tasks = current_user.tasks.in_date_order
    tasks = tasks.starred if @starred
    @tasks_by_tag = tasks.group_by(&:tag_id)
    @today = Time.current.in_time_zone(current_user.zone).to_date
    @task = task || current_user.tasks.new(tag_id: session[:last_tag_id])
  end

  def task_params
    params.require(:task).permit(:description, :tag_id, :due_date)
  end
end
