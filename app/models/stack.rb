class Stack < ActiveRecord::Base

  has_many :lists, dependent: :destroy
  
  validates_presence_of :token
  validates_uniqueness_of :token

  before_validation :generate_token, on: :create 
  
  def to_param
    token
  end

  protected

    def generate_token
      self.token = SecureRandom.urlsafe_base64(32) if new_record? && token.nil?
    end
end
