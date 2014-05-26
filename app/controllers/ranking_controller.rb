class RankingController < ApplicationController
	def show_ranking
		@quinielas = Quiniela.where('').order("points ASC")
		@active_ranking = 'active'
		@session = current_user
  		if !@session
  			redirect_to "/"
  		end
	end
end
