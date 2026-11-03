class DeviceAuthorization < ActiveRecord::Base
  validates :name, presence: true, length: { maximum: 100 }

  def self.issue!(name)
    token = SecureRandom.hex(32)
    authorization = create!(name: name, token_digest: AndroidDevice.digest(token),
      user_code: SecureRandom.hex(6).upcase, expires_at: 10.minutes.from_now)
    [authorization, token]
  end

  def approve!(user)
    with_lock do
      raise ActiveRecord::RecordNotFound if expires_at <= Time.current
      device = user.android_devices.create!(name: name, token_digest: token_digest)
      destroy!
      device
    end
  end
end
