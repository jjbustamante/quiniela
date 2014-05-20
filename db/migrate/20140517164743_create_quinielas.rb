class CreateQuinielas < ActiveRecord::Migration
  def change
    create_table :quinielas do |t|
    	t.string :name
      t.integer :points
      t.date :last_update
      t.timestamps
      t.references :user
    end
    add_index :quinielas, :user_id
  end
end
