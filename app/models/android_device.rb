class AndroidDevice < ActiveRecord::Base
  belongs_to :user
  validates :name, presence: true, length: { maximum: 100 }
  validates :token_digest, presence: true, uniqueness: true

  def self.digest(token)
    Digest::SHA256.hexdigest(token)
  end

  def self.for_token(token)
    return unless token.is_a?(String) && token.match?(/\A[0-9a-f]{64}\z/)

    device = find_by(token_digest: digest(token))
    device if device&.user&.access_allowed?
  end
end
