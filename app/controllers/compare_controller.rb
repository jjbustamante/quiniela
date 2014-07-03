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
		#@bets = Bet.where(match_id: params[:id]).order('public.quinielas.points DESC')
		@is_16round_match = false
		if params[:id].to_f <= 48
			@bets = Bet.joins(:quiniela).joins(:team1).where(match_id: params[:id]).order("quinielas.points DESC")
		else
			@bets = Bet.joins(:quiniela).joins(:team1).where(match_id: params[:id]).order("bets.team1_id ASC,bets.team2_id ASC")
			@is_16round_match = true
		end
		
		@match = Match.find(params[:id])
		@played = @match.played == 1
		@winner_id = @match.winner_id
	end
end
