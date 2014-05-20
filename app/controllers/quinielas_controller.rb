class QuinielasController < ApplicationController
	def new
		#deadline = DEADLINE
		
		if DateTime.now < APP_CONFIG['deadline_fase_grupos'].to_datetime
			@quiniela = Quiniela.new
			@matches = Match.where('').order("id ASC")
			@session = current_user
			@active_matches = 'active'	
			@bets = []
			@quiniela.user = current_user
			@matches.each do |m|
				bet = @quiniela.bets.build
				bet.match = m
				@bets.push bet
			end

		else
			flash[:success] = "¡La creacion de quinielas ya ha cerrado!"
			redirect_to '/'
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
