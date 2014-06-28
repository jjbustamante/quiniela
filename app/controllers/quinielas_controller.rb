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
			@group_stage_open = false
			@round_of_16_open = false
			@bloq_message = "Para mantener la competitividad de la quiniela las apuestas en la segunda fase del torneo estarán abiertas desde el 26/06/2014 a las 5:30pm al 28/06/2014 a las 11:30am, de igual manera te notificaremos vía correo electrónico."
		
		else
			flash[:success] = "La creacion de quinielas ya ha cerrado"
			redirect_to '/quinielas/show'
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
		@session = current_user
		@quiniela = Quiniela.find(params[:id])
	end

	def delete
		if DateTime.now < APP_CONFIG['deadline_fase_grupos'].to_datetime
			@quiniela = Quiniela.find(params[:id])
			if @quiniela.user.id == current_user.id
				@quiniela.destroy
			end
			flash[:success] = "Tu quiniela se ha eliminado"
			redirect_to '/quinielas/show'
		else
			flash[:success] = "¡Ya ha iniciado el torneo, no puedes eliminar tu quiniela!"
			redirect_to '/quinielas/show'
		end
	end

	def edit
		if DateTime.now < APP_CONFIG['deadline_fase_grupos'].to_datetime
			@group_stage_open = true
			@round_of_16_open = false
			@bloq_message = "Para mantener la competitividad de la quiniela las apuestas en la segunda fase del torneo estarán abiertas desde el 26/06/2014 a las 5:30pm al 28/06/2014 a las 11:30am, de igual manera te notificaremos vía correo electrónico."
		else
			if DateTime.now < APP_CONFIG['opening_2da_fase'].to_datetime
				@group_stage_open = false
				@round_of_16_open = false
				@bloq_message = "Para mantener la competitividad de la quiniela las apuestas en la segunda fase del torneo estarán abiertas desde el 26/06/2014 a las 5:30pm al 28/06/2014 a las 11:30am, de igual manera te notificaremos vía correo electrónico."
				@bloq_message2 = "El torneo ha iniciado, ya no se pueden modificar las apuestas de la fase de grupos."
			else
				if DateTime.now < APP_CONFIG['deadline_2da_fase'].to_datetime
					@group_stage_open = false
					@round_of_16_open = true
					@bloq_message2 = "El torneo ha iniciado, ya no se pueden modificar las apuestas de la fase de grupos."
			
				else
					@group_stage_open = false
					@round_of_16_open = false
					if DateTime.now < APP_CONFIG['deadline_fin_torneo'].to_datetime
						@bloq_message = "El torneo ha iniciado, ya no se pueden modificar las apuestas de la segunda fase."
						@bloq_message2 = "El torneo ha iniciado, ya no se pueden modificar las apuestas de la fase de grupos."
					else
						@bloq_message = "El torneo ha culminado. Gracias por participar en la quiniela 2014"
						@bloq_message2 = "El torneo ha culminado. Gracias por participar en la quiniela 2014"			
					end


				end
			end
		end

		#if DateTime.now < APP_CONFIG['deadline_fase_grupos'].to_datetime
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

		#else
		#	flash[:success] = "¡Ya ha iniciado el torneo, no puede modificar sus apuestas de la fase de grupos!"
		#	redirect_to '/'
		#end
	end

	def update
		if DateTime.now < APP_CONFIG['deadline_2da_fase'].to_datetime
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

			flash[:success] = "¡Ya ha iniciado el torneo, no puedes modificar las apuestas de la fase de grupos!"
			redirect_to '/'
		end
	end

	def show
		@user_session = UserSession.new
	  	@session = current_user
	  	@active_index = 'active'
	  	@can_create_quiniela = DateTime.now < APP_CONFIG['deadline_fase_grupos'].to_datetime
	  	if @session
	  		@quinielas = Quiniela.where(user_id: @session.id).order("id ASC")
	  	else
  			redirect_to "/"
		end
	end
	def brackets
	end
end
