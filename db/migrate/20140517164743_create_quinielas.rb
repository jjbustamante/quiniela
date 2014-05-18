class CreateQuinielas < ActiveRecord::Migration
  def change
    create_table :quinielas do |t|
    	t.string :name
      t.integer :points
      t.date :last_update
      t.timestamps
      t.integer :user_id
    end
    add_index :quinielas, :user_id
  end
end
