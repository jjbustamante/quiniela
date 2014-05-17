class MatchesController < ApplicationController
	def new
	end
	def show_matches
			@matches = Match.where('').order("id ASC")
			@session = current_user
			@active_matches = 'active'
	end
end
