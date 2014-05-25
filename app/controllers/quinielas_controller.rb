class QuinielasController < ApplicationController
	def new
		if DateTime.now < APP_CONFIG['deadline_fase_grupos'].to_datetime
			@quiniela = Quiniela.new
			@matches = Match.where(round_id: 1).order("id ASC")
			@session = current_user
			@active_matches = 'active'	
			@bets = []
			@quiniela.user = User.new
			@quiniela.user.id = @session.id
			@matches.each do |m|
				bet = @quiniela.bets.build
				bet.match = m
				@bets.push bet
			end
			@oct_matches = Match.where(round_id: 2)
		else
			flash[:success] = "¡La creacion de quinielas ya ha cerrado!"
			redirect_to '/'
		end
		
	end

	def create
	    @quiniela = Quiniela.new(params[:quiniela])
		@session = current_user
		@quiniela.user = User.new
		@quiniela.user.id = @session.id
	    if @quiniela.save
	      flash[:success] = "¡Bienvenido de nuevo!"
	      redirect_back_or_default root_url
	    else
	      render :action => :new
	    end
	  end

	def show_quinielas
		
	end
	def show
		@user_session = UserSession.new
	  	@session = current_user
	  	@active_index = 'active'
	  	if @session
	  		@quinielas = Quiniela.where(user_id: @session.id).order("points DESC")
	  	else
  			redirect_to "/"
		end
	end

end
