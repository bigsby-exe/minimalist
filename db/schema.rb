# This file is auto-generated from the current state of the database. Instead
# of editing this file, please use the migrations feature of Active Record to
# incrementally modify your database, and then regenerate this schema definition.
#
# This file is the source Rails uses to define your schema when running `bin/rails
# db:schema:load`. When creating a new database, `bin/rails db:schema:load` tends to
# be faster and is potentially less error prone than running all of your
# migrations from scratch. Old migrations may fail to apply correctly if those
# migrations use external dependencies or application code.
#
# It's strongly recommended that you check this file into your version control system.

ActiveRecord::Schema[8.1].define(version: 2026_10_18_010000) do
  create_table "identities", charset: "utf8mb3", force: :cascade do |t|
    t.datetime "created_at", null: false
    t.string "provider"
    t.string "uid"
    t.datetime "updated_at", null: false
    t.bigint "user_id"
    t.index ["user_id"], name: "index_identities_on_user_id"
  end

  create_table "lists", charset: "utf8mb3", force: :cascade do |t|
    t.datetime "created_at", null: false
    t.datetime "deleted_at"
    t.string "name"
    t.bigint "owner_id"
    t.string "slug"
    t.integer "sort_order"
    t.bigint "stack_id"
    t.datetime "updated_at", null: false
    t.index ["owner_id"], name: "fk_rails_3884c760f7"
  end

  create_table "lists_users", id: false, charset: "utf8mb3", force: :cascade do |t|
    t.bigint "list_id", null: false
    t.bigint "user_id", null: false
    t.index ["list_id", "user_id"], name: "index_lists_users_on_list_id_and_user_id", unique: true
    t.index ["user_id", "list_id"], name: "index_lists_users_on_user_id_and_list_id"
  end

  create_table "stacks", charset: "utf8mb3", force: :cascade do |t|
    t.datetime "created_at", null: false
    t.string "token"
    t.datetime "updated_at", null: false
    t.index ["token"], name: "index_stacks_on_token", unique: true
  end

  create_table "stats", charset: "utf8mb3", force: :cascade do |t|
    t.datetime "created_at", null: false
    t.integer "lists_count"
    t.integer "tasks_count"
    t.datetime "updated_at", null: false
  end

  create_table "tags", charset: "utf8mb4", collation: "utf8mb4_0900_ai_ci", force: :cascade do |t|
    t.string "color", null: false
    t.datetime "created_at", null: false
    t.string "emoji", null: false
    t.string "name", null: false
    t.integer "position", default: 0, null: false
    t.datetime "updated_at", null: false
    t.bigint "user_id", null: false
    t.index ["user_id", "name"], name: "index_tags_on_user_id_and_name", unique: true
    t.index ["user_id"], name: "index_tags_on_user_id"
  end

  create_table "tasks", charset: "utf8mb4", collation: "utf8mb4_unicode_ci", force: :cascade do |t|
    t.datetime "created_at", null: false
    t.string "description"
    t.date "due_date"
    t.datetime "starred_until"
    t.bigint "tag_id", null: false
    t.datetime "updated_at", null: false
    t.bigint "user_id", null: false
    t.index ["tag_id"], name: "index_tasks_on_tag_id"
    t.index ["user_id"], name: "index_tasks_on_user_id"
  end

  create_table "users", charset: "utf8mb3", force: :cascade do |t|
    t.string "api_token"
    t.datetime "created_at", null: false
    t.datetime "current_sign_in_at"
    t.string "current_sign_in_ip"
    t.string "email"
    t.string "image_url"
    t.datetime "last_sign_in_at"
    t.string "last_sign_in_ip"
    t.string "name"
    t.datetime "remember_created_at"
    t.integer "sign_in_count", default: 0, null: false
    t.string "time_zone"
    t.datetime "updated_at", null: false
    t.index ["email"], name: "index_users_on_email"
  end

  add_foreign_key "identities", "users"
  add_foreign_key "lists", "users", column: "owner_id"
  add_foreign_key "tags", "users"
  add_foreign_key "tasks", "tags"
  add_foreign_key "tasks", "users"
end
