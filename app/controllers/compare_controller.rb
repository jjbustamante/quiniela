class CompareController < ApplicationController
	def show
		@session = current_user
		@active_index = 'active'
		@quiniela = Quiniela.find(params[:id])
		@matches = Match.where("").order("id ASC")

	end
end
