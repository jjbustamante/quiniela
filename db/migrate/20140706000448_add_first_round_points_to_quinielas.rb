class AddFirstRoundPointsToQuinielas < ActiveRecord::Migration
  def change
    add_column :quinielas, :first_round_points, :int
  end
end
