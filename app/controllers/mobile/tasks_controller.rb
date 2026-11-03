module Mobile
  class TasksController < BaseController
    def create
      attributes = params.require(:task).permit(:description, :tag_id, :due_date).to_h
      starred = params[:starred] == true
      request_id = params.require(:request_id).to_s
      unless request_id.match?(/\A[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\z/)
        return render json: { error: "Invalid request ID." }, status: :unprocessable_entity
      end
      digest = Digest::SHA256.hexdigest([attributes.sort, starred].to_json)

      # Serialize retries across devices/workers; the receipt and task commit together.
      current_user.with_lock do
        receipt = current_user.mobile_task_submissions.find_by(request_id: request_id)
        if receipt
          if receipt.payload_digest != digest
            render json: { error: "This submission already used different task details." }, status: :conflict
          elsif current_user.tasks.exists?(receipt.task_id)
            render json: { id: receipt.task_id }, status: :ok
          else
            render json: { error: "This task was already added and then binned. Discard this draft." }, status: :gone
          end
        else
          task = current_user.tasks.create!(attributes.merge(starred_until: starred ? current_user.next_midnight : nil))
          current_user.mobile_task_submissions.create!(request_id: request_id, payload_digest: digest, task_id: task.id)
          render json: { id: task.id }, status: :created
        end
      end
    end

    def unstar
      current_user.tasks.find(params[:id]).update!(starred_until: nil)
      head :no_content
    end
  end
end
