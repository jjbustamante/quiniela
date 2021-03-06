# encoding: UTF-8
# This file is auto-generated from the current state of the database. Instead
# of editing this file, please use the migrations feature of Active Record to
# incrementally modify your database, and then regenerate this schema definition.
#
# Note that this schema.rb definition is the authoritative source for your
# database schema. If you need to create the application database on another
# system, you should be using db:schema:load, not running all the migrations
# from scratch. The latter is a flawed and unsustainable approach (the more migrations
# you'll amass, the slower it'll run and the greater likelihood for issues).
#
# It's strongly recommended to check this file into your version control system.

ActiveRecord::Schema.define(:version => 20140518153545) do

  create_table "bets", :force => true do |t|
    t.integer  "score_t1"
    t.integer  "score_t2"
    t.datetime "created_at",  :null => false
    t.datetime "updated_at",  :null => false
    t.integer  "winner_id"
    t.integer  "match_id"
    t.integer  "quiniela_id"
    t.integer  "team1_id"
    t.integer  "team2_id"
  end

  create_table "matches", :force => true do |t|
    t.string   "winner_name"
    t.integer  "score_t1"
    t.integer  "score_t2"
    t.datetime "match_date"
    t.datetime "created_at",                      :null => false
    t.datetime "updated_at",                      :null => false
    t.integer  "winner_id"
    t.integer  "match_parent1_id"
    t.integer  "match_parent2_id"
    t.integer  "round_id"
    t.integer  "team1_id"
    t.integer  "team2_id"
    t.integer  "played",           :default => 0
    t.integer  "editable",         :default => 0
  end

  create_table "quinielas", :force => true do |t|
    t.string   "name"
    t.integer  "points"
    t.date     "last_update"
    t.datetime "created_at",  :null => false
    t.datetime "updated_at",  :null => false
    t.integer  "user_id"
  end

  add_index "quinielas", ["user_id"], :name => "index_quinielas_on_user_id"

  create_table "rounds", :force => true do |t|
    t.string   "name"
    t.integer  "status"
    t.date     "start_date"
    t.date     "end_date"
    t.integer  "round_type"
    t.datetime "created_at", :null => false
    t.datetime "updated_at", :null => false
  end

  create_table "teams", :force => true do |t|
    t.string   "name"
    t.string   "code"
    t.string   "group"
    t.datetime "created_at",                :null => false
    t.datetime "updated_at",                :null => false
    t.integer  "status",     :default => 0
  end

  create_table "user_sessions", :force => true do |t|
    t.string   "session_id", :null => false
    t.text     "data"
    t.datetime "created_at", :null => false
    t.datetime "updated_at", :null => false
  end

  add_index "user_sessions", ["session_id"], :name => "index_user_sessions_on_session_id"
  add_index "user_sessions", ["updated_at"], :name => "index_user_sessions_on_updated_at"

  create_table "users", :force => true do |t|
    t.string   "name",                :default => "", :null => false
    t.string   "login",                               :null => false
    t.string   "crypted_password",                    :null => false
    t.string   "password_salt",                       :null => false
    t.string   "email",                               :null => false
    t.string   "persistence_token",                   :null => false
    t.string   "single_access_token",                 :null => false
    t.string   "perishable_token",                    :null => false
    t.integer  "login_count",         :default => 0,  :null => false
    t.integer  "failed_login_count",  :default => 0,  :null => false
    t.datetime "last_request_at"
    t.datetime "current_login_at"
    t.datetime "last_login_at"
    t.string   "current_login_ip"
    t.string   "last_login_ip"
    t.datetime "created_at",                          :null => false
    t.datetime "updated_at",                          :null => false
    t.integer  "is_admin",            :default => 0,  :null => false
    t.string   "photo_file_name"
    t.string   "photo_content_type"
    t.integer  "photo_file_size"
    t.datetime "photo_updated_at"
  end

end
