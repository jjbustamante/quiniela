class CreateRounds < ActiveRecord::Migration
  def change
    create_table :rounds do |t|
   	  t.string :name
      t.integer :status
      t.date :start_date
      t.date :end_date
      t.integer :round_type
      t.timestamps
  end
    Round.create :name => 'Fase de grupos', :status => 1, :start_date => '2013-10-03 15:50:01.246', :end_date => '2014-10-03 15:50:01.246', :round_type => 0
    Round.create :name => 'Octavos de final', :status => 0, :start_date => '2013-10-03 15:50:01.246', :end_date => '2014-10-03 15:50:01.246', :round_type => 1
    Round.create :name => 'Cuartos de final', :status => 0, :start_date => '2013-10-03 15:50:01.246', :end_date => '2014-10-03 15:50:01.246', :round_type => 1
    Round.create :name => 'Semifinales', :status => 0, :start_date => '2013-10-03 15:50:01.246', :end_date => '2014-10-03 15:50:01.246', :round_type => 1
    Round.create :name => '3er lugar', :status => 0, :start_date => '2013-10-03 15:50:01.246', :end_date => '2014-10-03 15:50:01.246', :round_type => 1
    Round.create :name => 'Final', :status => 0, :start_date => '2013-10-03 15:50:01.246', :end_date => '2014-10-03 15:50:01.246', :round_type => 1
    end    
end
