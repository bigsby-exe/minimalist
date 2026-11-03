class User < ActiveRecord::Base
  has_many :tasks, dependent: :destroy
  has_many :tags, dependent: :destroy
  has_many :android_devices, dependent: :destroy
  has_many :mobile_task_submissions, dependent: :destroy
  validates :time_zone, inclusion: { in: TZInfo::Timezone.all_identifiers }, allow_nil: true
  after_create :create_default_tags

  def zone
    ActiveSupport::TimeZone[time_zone || "Etc/UTC"]
  end

  def next_midnight
    Time.current.in_time_zone(zone).tomorrow.beginning_of_day
  end

  def create_default_tags
    [["Household", "🏠", "#dcecff"], ["Medical", "🩺", "#fbe0e6"], ["Work", "💼", "#e0efdd"]].each_with_index do |(name, emoji, color), index|
      tags.create!(name: name, emoji: emoji, color: color, position: index)
    end
  end
  validates :email, format: /@/

  # Include default devise modules. Others available are:
  devise :rememberable, :trackable, :omniauthable, omniauth_providers: [:google_oauth2]

  before_create :generate_api_token

  def self.allowed_email?(email)
    normalized = email.to_s.strip.downcase
    normalized.present? && ENV.fetch("MINIMALIST_ALLOWED_EMAILS", "").split(",").map { |value| value.strip.downcase }.include?(normalized)
  end

  def access_allowed?
    self.class.allowed_email?(email)
  end

  # Grab a user by their identity or create a new one
  # @see http://sourcey.com/rails-4-omniauth-using-devise-with-twitter-facebook-and-linkedin/
  def self.from_omniauth(auth)
    # The Google strategy exposes only verified addresses in info.email.
    return unless auth.provider == "google_oauth2" && allowed_email?(auth.info.email)

    # Get the identity and user if they exist
    identity = Identity.find_for_oauth(auth)

    user = identity.user
    return if user && !user.access_allowed?

    if user.nil?
      # Get the existing user by email if the provider gives us a verified email.
      email = auth.info.email

      # Create the user if it's a new registration
      user = User.where(email: email).first_or_create do |user|
        user.name = auth.info.name
        user.image_url = auth.info.image
        user.email = email
      end
    else
      # update the users image when they log in and they have a new avatar image
      if auth.info.image.present? && auth.info.image != user.image_url
        user.update_attribute(:image_url, auth.info.image)
      end

      if auth.info.name.present? && auth.info.name != user.name
        user.update_attribute(:name, auth.info.name)
      end
    end

    # Associate the identity with the user if needed
    if identity.user != user
      identity.user = user
      identity.save!
    end

    # Return the user
    user
  end

  private

    def generate_api_token
      return if api_token.present?
      
      loop do
        self.api_token = SecureRandom.base64(64)
        break unless User.find_by(api_token: api_token)
      end
    end
  
end
