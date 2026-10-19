class Tag < ActiveRecord::Base
  COLORS = { "Pastel blue" => "#dcecff", "Pastel pink" => "#fbe0e6", "Pastel green" => "#e0efdd", "Pastel lavender" => "#e9e1fa", "Pastel yellow" => "#fff0c7", "Pastel peach" => "#ffe3cf" }.freeze
  belongs_to :user
  has_many :tasks, dependent: :restrict_with_error
  validates :name, presence: true, length: { maximum: 50 }, uniqueness: { scope: :user_id }
  validates :emoji, presence: true, length: { maximum: 16 }
  validates :color, inclusion: { in: COLORS.values }
  validates :position, numericality: { only_integer: true, greater_than_or_equal_to: 0 }
  scope :in_profile_order, -> { order(:position, :id) }
end
