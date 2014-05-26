class QuinielasController < ApplicationController
	def new
		if DateTime.now < APP_CONFIG['deadline_fase_grupos'].to_datetime
			@quiniela = Quiniela.new
			@matches = Match.where("").order("id ASC")
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
			@next_round_available = false
		else
			flash[:success] = "¡La creacion de quinielas ya ha cerrado!"
			redirect_to '/'
		end
	end

	def create
		if DateTime.now < APP_CONFIG['deadline_fase_grupos'].to_datetime
		    @quiniela = Quiniela.new(params[:quiniela])
			@session = current_user
			@quiniela.user = User.new
			@quiniela.user.id = @session.id
			@active_index = 'active'
		    if @quiniela.save
		      flash[:success] = "¡Quniela creada con exito!"
		      redirect_to '/quinielas/show'
		    else
		      render :action => :new
		    end
	    else
			flash[:success] = "¡La creacion de quinielas ya ha cerrado!"
			redirect_to '/'
		end
	  end

	def show_quinielas
		@quiniela = Quiniela.find(params[:id])
	end

	def showro
		@quiniela = Quiniela.find(params[:id])
	end

	def delete
		if DateTime.now < APP_CONFIG['deadline_fase_grupos'].to_datetime
			@quiniela = Quiniela.find(params[:id])
			if @quiniela.user.id == current_user.id
				@quiniela.destroy
			end
		else
			flash[:success] = "¡Ya ha iniciado el torneo, no puede eliminar su quiniela!"
			redirect_to '/'
		end
		redirect_back_or_default root_url
	end

	def edit

		if DateTime.now < APP_CONFIG['deadline_fase_grupos'].to_datetime
			@session = current_user
			@active_index = 'active'
			@quiniela = Quiniela.find(params[:id])
			if @quiniela.user.id != current_user.id
				redirect_back_or_default root_url
			end
			@bets = []
			if @quiniela.bets.size <= 48
				@matches = Match.where(round_id: [2,3,4,5,6]).order("id ASC")
				@matches.each do |m|
					bet = @quiniela.bets.build
					bet.match = m
					@bets.push bet
				end
			end

			@oct_matches = Match.where(round_id: 2)
			@next_round_available = false
		else
			flash[:success] = "¡Ya ha iniciado el torneo, no puede modificar sus apuestas de la fase de grupos!"
			redirect_to '/'
		end
	end

	def update
		if DateTime.now < APP_CONFIG['deadline_fase_grupos'].to_datetime
			@quiniela = Quiniela.find(params[:id])
			if @quiniela.user.id != current_user.id
				redirect_back_or_default root_url
			end	
			if @quiniela.update_attributes(params[:quiniela])
	      		flash[:success] = "Quiniela modificada exitosamente."
				redirect_to '/quinielas/show'
			else
			    render 'edit'
			end
		else
			flash[:success] = "¡Ya ha iniciado el torneo, no puede modificar sus apuestas de la fase de grupos!"
			redirect_to '/'
		end
	end

	def show
		@user_session = UserSession.new
	  	@session = current_user
	  	@active_index = 'active'
	  	if @session
	  		@quinielas = Quiniela.where(user_id: @session.id).order("id ASC")
	  	else
  			redirect_to "/"
		end
	end
	def brackets
	end
end
