class QuinielasController < ApplicationController
	def new
		@matches = Match.where('').order("id ASC")
		@session = current_user
		@active_matches = 'active'	
	end

	def create
			render plain: params[:quiniela].inspect
	end

	def show_quinielas
		
	end
end
