class CompareController < ApplicationController
	def show
		@session = current_user
		@active_index = 'active'
		@quiniela = Quiniela.find(params[:id])
		@matches = Match.where("").order("id ASC")

	end
	def compare_by_bet
		@session = current_user
		@active_index = 'active'
		@bets = Bet.where(match_id: params[:id])
		@match = Match.find(params[:id])
		@played = @match.played == 1
		@winner_id = @match.winner_id
	end
end
