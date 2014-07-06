class RankingController < ApplicationController
	def show_ranking
		@quinielas = Quiniela.where('').order("points DESC")
		@active_ranking = 'active'
		@session = current_user
  		if !@session
  			redirect_to "/"
  		end
	end
	def show_ranking2
		@quinielas = Quiniela.where('').order("(points - first_round_points) DESC")
		@active_ranking = 'active'
		@session = current_user
  		if !@session
  			redirect_to "/"
  		end
	end
	def show_ranking3
		@quinielas = Quiniela.where('').order("(first_round_points) DESC")
		@active_ranking = 'active'
		@session = current_user
  		if !@session
  			redirect_to "/"
  		end
	end
end
