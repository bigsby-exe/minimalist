class AddUniqueIndexToStackTokens < ActiveRecord::Migration[8.1]
  def change
    add_index :stacks, :token, unique: true
  end
end
