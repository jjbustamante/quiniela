class QuinielasController < ApplicationController
	def new
		@quiniela = Quiniela.new
		@matches = Match.where('').order("id ASC")
		@session = current_user
		@active_matches = 'active'	
		@matches.each do |m|
			bet = @quiniela.bets.build
			bet.match = m
		end
	end

	def create
			render plain: params[:quiniela].inspect
	end

	def show_quinielas
		
	end
end
