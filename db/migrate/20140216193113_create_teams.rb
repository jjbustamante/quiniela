class CreateTeams < ActiveRecord::Migration
  def change
    create_table :teams do |t|
      t.string :name
      t.string :code
      t.string :group
      t.timestamps
    end
    Team.create :name => 'Brasil', :code => 'bra', :group => 'A'
    Team.create :name => 'Croacia', :code => 'cro', :group => 'A'
    Team.create :name => 'México', :code => 'mex', :group => 'A'
    Team.create :name => 'Camerún', :code => 'cam', :group => 'A'
    
    Team.create :name => 'España', :code => 'esp', :group => 'B'
    Team.create :name => 'Paises Bajos', :code => 'pai', :group => 'B'
    Team.create :name => 'Chile', :code => 'chi', :group => 'B'
    Team.create :name => 'Australia', :code => 'aus', :group => 'B'
    
    Team.create :name => 'Colombia', :code => 'col', :group => 'C'
    Team.create :name => 'Grecia', :code => 'gre', :group => 'C'
    Team.create :name => 'Costa de Marfil', :code => 'cos', :group => 'C'
    Team.create :name => 'Japón', :code => 'jap', :group => 'C'
    
    Team.create :name => 'Uruguay', :code => 'uru', :group => 'D'
    Team.create :name => 'Costa Rica', :code => 'ctr', :group => 'D'
    Team.create :name => 'Inglaterra', :code => 'ing', :group => 'D'
    Team.create :name => 'Italia', :code => 'ita', :group => 'D'
  
    Team.create :name => 'Suiza', :code => 'sui', :group => 'E'
    Team.create :name => 'Ecuador', :code => 'ecu', :group => 'E'
    Team.create :name => 'Francia', :code => 'fra', :group => 'E'
    Team.create :name => 'Honduras', :code => 'hon', :group => 'E'

        Team.create :name => 'Argentina', :code => 'arg', :group => 'F'
    Team.create :name => 'Boznia y Herzegovina', :code => 'boz', :group => 'F'
    Team.create :name => 'Irán', :code => 'ira',  :group => 'F'
    Team.create :name => 'Nigeria', :code => 'nig', :group => 'F'

        Team.create :name => 'Alemania', :code => 'ale', :group => 'G'
    Team.create :name => 'Portugal', :code => 'por', :group => 'G'
    Team.create :name => 'Ghana', :code => 'gha', :group => 'G'
    Team.create :name => 'Estados Unidos', :code => 'usa', :group => 'G'   

        Team.create :name => 'Bélgica', :code => 'bel', :group => 'H'
    Team.create :name => 'Argelia', :code => 'argl', :group => 'H'
    Team.create :name => 'Rusia', :code => 'rus', :group => 'H'
    Team.create :name => 'Corea del Sur', :code => 'cor', :group => 'H'
  end
end
