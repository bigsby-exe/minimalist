class UserSerializer < ActiveModel::Serializer
  attributes :id, :name, :image_url, :is_owner
  attribute :email, if: :include_email?

  def is_owner
    list = instance_options[:list]
    object.owns_list?(list)
  end

  def include_email?
    list = instance_options[:list]
    list.present? && scope.present? && list.owned_by?(scope.current_user)
  end
end
