class MatchesController < ApplicationController
	def new
	end
	def show_matches
			@matches = Match.where('').order("id ASC")
			@session = current_user
			@active_matches = 'active'
			@teamsA = Team.where(group: 'A')
		  	@teamsB = Team.where(group: 'B')
			@teamsC = Team.where(group: 'C')
			@teamsD = Team.where(group: 'D')
			@teamsE = Team.where(group: 'E')
			@teamsF = Team.where(group: 'F')
			@teamsG = Team.where(group: 'G')
			@teamsH = Team.where(group: 'H')
			if !@session
  				redirect_to "/"
  			end
	end
end
