class Task < ActiveRecord::Base
  belongs_to :user
  belongs_to :tag
  validates :description, presence: true, length: { maximum: 255 }
  validate :tag_belongs_to_user
  validate do
    if due_date_before_type_cast.present? && due_date.nil?
      errors.add(:due_date, "must be a valid calendar date")
    end
  end

  scope :in_date_order, -> { order(Arel.sql("due_date IS NULL"), :due_date, :created_at, :id) }
  scope :starred, -> { where("starred_until > ?", Time.current) }

  def starred?
    starred_until.present? && starred_until > Time.current
  end

  def urgency(today)
    return "" unless due_date
    return "‼️" if due_date < today
    due_date == today ? "❗" : ""
  end

  private

  def tag_belongs_to_user
    errors.add(:tag, "must belong to you") unless tag && tag.user_id == user_id
  end
end
