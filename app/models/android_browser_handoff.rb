class AndroidBrowserHandoff < ActiveRecord::Base
  belongs_to :user
  validates :purpose, inclusion: { in: %w[open sign_out] }
  validates :destination, format: { with: /\A\/dashboard(?:\?view=starred)?(?:#task-[1-9][0-9]*)?\z/ }

  def self.issue!(device, purpose:, destination: "/dashboard")
    where("expires_at <= ?", Time.current).delete_all
    token = SecureRandom.hex(32)
    create!(user: device.user, android_device_id: device.id, purpose: purpose,
      destination: destination, token_digest: AndroidDevice.digest(token), expires_at: 5.minutes.from_now)
    token
  end

  def self.pending(token)
    return unless token.to_s.match?(/\A[0-9a-f]{64}\z/)
    where("expires_at > ?", Time.current).find_by(token_digest: AndroidDevice.digest(token))
  end
end
