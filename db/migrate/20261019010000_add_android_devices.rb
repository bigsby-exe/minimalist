class AddAndroidDevices < ActiveRecord::Migration[8.1]
  def change
    create_table :android_devices, charset: "utf8mb4", collation: "utf8mb4_unicode_ci" do |t|
      t.references :user, null: false, foreign_key: true
      t.string :name, null: false, limit: 100
      t.string :token_digest, null: false, limit: 64
      t.timestamps
    end
    add_index :android_devices, :token_digest, unique: true

    create_table :device_authorizations, charset: "utf8mb4", collation: "utf8mb4_unicode_ci" do |t|
      t.string :name, null: false, limit: 100
      t.string :token_digest, null: false, limit: 64
      t.string :user_code, null: false, limit: 12
      t.datetime :expires_at, null: false
      t.timestamps
    end
    add_index :device_authorizations, :token_digest, unique: true
    add_index :device_authorizations, :user_code, unique: true
    add_index :device_authorizations, :expires_at

    # Retain the receipt after binning so a delayed retry cannot recreate a task.
    create_table :mobile_task_submissions do |t|
      t.references :user, null: false, foreign_key: true
      t.string :request_id, null: false, limit: 36
      t.string :payload_digest, null: false, limit: 64
      t.bigint :task_id, null: false
      t.timestamps
    end
    add_index :mobile_task_submissions, [:user_id, :request_id], unique: true
  end
end
