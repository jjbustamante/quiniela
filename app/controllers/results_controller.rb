class ResultsController < ApplicationController
	def show_results
		@matches = Match.where('').order("id ASC")
		@rounds = Round.all
		@session = current_user
		@active_results = 'active'
		if !@session
  			redirect_to "/"
  		end	
	end
end
