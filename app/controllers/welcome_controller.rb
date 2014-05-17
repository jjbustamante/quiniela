class WelcomeController < ApplicationController
  def index
  	@user_session = UserSession.new

  	@session = current_user

  	@teamsA = Team.where(group: 'A')
  	@teamsB = Team.where(group: 'B')
	@teamsC = Team.where(group: 'C')
	@teamsD = Team.where(group: 'D')
	@teamsE = Team.where(group: 'E')
	@teamsF = Team.where(group: 'F')
	@teamsG = Team.where(group: 'G')
	@teamsH = Team.where(group: 'H')

	@active_index = 'active'

  end
end
