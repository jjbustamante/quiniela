class CreateTeams < ActiveRecord::Migration
  def change
    create_table :teams do |t|
      t.string :name
      t.string :code
      t.string :group
      t.timestamps
      t.integer :status, :default => 0
    end
    Team.create :name => 'Brasil', :code => 'bra', :group => 'A'
    Team.create :name => 'Croacia', :code => 'cro', :group => 'A'
    Team.create :name => 'México', :code => 'mex', :group => 'A'
    Team.create :name => 'Camerún', :code => 'cmr', :group => 'A'
    
    Team.create :name => 'España', :code => 'esp', :group => 'B'
    Team.create :name => 'Paises Bajos', :code => 'ned', :group => 'B'
    Team.create :name => 'Chile', :code => 'chi', :group => 'B'
    Team.create :name => 'Australia', :code => 'aus', :group => 'B'
    
    Team.create :name => 'Colombia', :code => 'col', :group => 'C'
    Team.create :name => 'Grecia', :code => 'gre', :group => 'C'
    Team.create :name => 'Costa de Marfil', :code => 'civ', :group => 'C'
    Team.create :name => 'Japón', :code => 'jpn', :group => 'C'
    
    Team.create :name => 'Uruguay', :code => 'uru', :group => 'D'
    Team.create :name => 'Costa Rica', :code => 'crc', :group => 'D'
    Team.create :name => 'Inglaterra', :code => 'eng', :group => 'D'
    Team.create :name => 'Italia', :code => 'ita', :group => 'D'
  
    Team.create :name => 'Suiza', :code => 'sui', :group => 'E'
    Team.create :name => 'Ecuador', :code => 'ecu', :group => 'E'
    Team.create :name => 'Francia', :code => 'fra', :group => 'E'
    Team.create :name => 'Honduras', :code => 'hon', :group => 'E'

        Team.create :name => 'Argentina', :code => 'arg', :group => 'F'
    Team.create :name => 'Boznia y Herzegovina', :code => 'bih', :group => 'F'
    Team.create :name => 'Irán', :code => 'irn',  :group => 'F'
    Team.create :name => 'Nigeria', :code => 'nga', :group => 'F'

        Team.create :name => 'Alemania', :code => 'ger', :group => 'G'
    Team.create :name => 'Portugal', :code => 'por', :group => 'G'
    Team.create :name => 'Ghana', :code => 'gha', :group => 'G'
    Team.create :name => 'Estados Unidos', :code => 'usa', :group => 'G'   

        Team.create :name => 'Bélgica', :code => 'bel', :group => 'H'
    Team.create :name => 'Argelia', :code => 'alg', :group => 'H'
    Team.create :name => 'Rusia', :code => 'rus', :group => 'H'
    Team.create :name => 'Corea del Sur', :code => 'kor', :group => 'H'

    Team.create :name => '1A', :code => 'none', :group => 'A', :status => 1
    Team.create :name => '2B', :code => 'none', :group => 'B', :status => 1
    Team.create :name => '1C', :code => 'none', :group => 'C', :status => 1
    Team.create :name => '2D', :code => 'none', :group => 'D', :status => 1
    Team.create :name => '1E', :code => 'none', :group => 'E', :status => 1
    Team.create :name => '2F', :code => 'none', :group => 'F', :status => 1
    Team.create :name => '1G', :code => 'none', :group => 'G', :status => 1
    Team.create :name => '2H', :code => 'none', :group => 'H', :status => 1

    Team.create :name => '1B', :code => 'none', :group => 'B', :status => 1
    Team.create :name => '2A', :code => 'none', :group => 'A', :status => 1
    Team.create :name => '1D', :code => 'none', :group => 'D', :status => 1
    Team.create :name => '2C', :code => 'none', :group => 'C', :status => 1
    Team.create :name => '1F', :code => 'none', :group => 'F', :status => 1
    Team.create :name => '2E', :code => 'none', :group => 'E', :status => 1
    Team.create :name => '1H', :code => 'none', :group => 'H', :status => 1
    Team.create :name => '2G', :code => 'none', :group => 'G', :status => 1

    Team.create :name => 'W49', :code => 'none', :group => 'none', :status => 1
    Team.create :name => 'W50', :code => 'none', :group => 'none', :status => 1
    Team.create :name => 'W51', :code => 'none', :group => 'none', :status => 1
    Team.create :name => 'W52', :code => 'none', :group => 'none', :status => 1
    Team.create :name => 'W53', :code => 'none', :group => 'none', :status => 1
    Team.create :name => 'W54', :code => 'none', :group => 'none', :status => 1
    Team.create :name => 'W55', :code => 'none', :group => 'none', :status => 1
    Team.create :name => 'W56', :code => 'none', :group => 'none', :status => 1
    Team.create :name => 'W57', :code => 'none', :group => 'none', :status => 1
    Team.create :name => 'W58', :code => 'none', :group => 'none', :status => 1
    Team.create :name => 'W59', :code => 'none', :group => 'none', :status => 1
    Team.create :name => 'W60', :code => 'none', :group => 'none', :status => 1
    Team.create :name => 'L61', :code => 'none', :group => 'none', :status => 1
    Team.create :name => 'L62', :code => 'none', :group => 'none', :status => 1
    Team.create :name => 'W61', :code => 'none', :group => 'none', :status => 1
    Team.create :name => 'W62', :code => 'none', :group => 'none', :status => 1

  end
end
