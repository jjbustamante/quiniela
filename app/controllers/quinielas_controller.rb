class QuinielasController < ApplicationController
	def new
		@quiniela = Quiniela.new
		@matches = Match.where('').order("id ASC")
		@session = current_user
		@active_matches = 'active'	
		@bets = []
		@quiniela.user = @session
		@matches.each do |m|
			bet = @quiniela.bets.build
			bet.match = m
			@bets.push bet
		end
	end

	def create
	    @quiniela = Quiniela.new(params[:quiniela])
	    if @quiniela.save
	      flash[:success] = "¡Bienvenido de nuevo!"
	      redirect_back_or_default root_url
	    else
	      render :action => :new
	    end
	  end

	def show_quinielas
		
	end
end
