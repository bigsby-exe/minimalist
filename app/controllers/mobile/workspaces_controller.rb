module Mobile
  class WorkspacesController < BaseController
    def show
      tags = current_user.tags.in_profile_order.to_a
      tasks = current_user.tasks.starred.in_date_order.group_by(&:tag_id)
      render json: {
        email: current_user.email, time_zone: current_user.time_zone || "Etc/UTC",
        fetched_at: Time.current.iso8601,
        tags: tags.map { |tag| tag.attributes.slice("id", "name", "emoji", "color") },
        tasks: tags.flat_map { |tag| (tasks[tag.id] || []).map { |task| task.attributes.slice("id", "description", "tag_id", "due_date", "starred_until") } }
      }
    end
  end
end
