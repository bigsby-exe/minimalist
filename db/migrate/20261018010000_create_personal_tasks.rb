class CreatePersonalTasks < ActiveRecord::Migration[8.1]
  def change
    add_column :users, :time_zone, :string
    create_table :tags, charset: "utf8mb4" do |t|
      t.references :user, null: false, foreign_key: true
      t.string :name, null: false
      t.string :emoji, null: false
      t.string :color, null: false
      t.integer :position, null: false, default: 0
      t.timestamps
    end
    add_index :tags, [:user_id, :name], unique: true
    # This fork has no user data to migrate from lists.
    remove_column :tasks, :list_id, :bigint
    remove_column :tasks, :completed, :boolean, default: false
    remove_column :tasks, :sort_order, :integer
    remove_column :tasks, :deleted_at, :datetime
    add_reference :tasks, :user, null: false, foreign_key: true
    add_reference :tasks, :tag, null: false, foreign_key: true
    add_column :tasks, :due_date, :date
    add_column :tasks, :starred_until, :datetime
    reversible do |dir|
      dir.up { execute "ALTER TABLE tasks CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci" }
    end
  end
end
