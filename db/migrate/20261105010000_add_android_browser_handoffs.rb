class AddAndroidBrowserHandoffs < ActiveRecord::Migration[8.1]
  def change
    create_table :android_browser_handoffs, charset: "utf8mb4" do |t|
      t.bigint :user_id, null: false
      t.bigint :android_device_id, null: false
      t.string :token_digest, limit: 64, null: false
      t.string :purpose, null: false
      t.string :destination, null: false, default: "/dashboard"
      t.datetime :expires_at, null: false
      t.index :token_digest, unique: true
      t.index :expires_at
    end
  end
end
