class QuinielasController < ApplicationController
	def new
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
		@quiniela = Quiniela.find(params[:id])
	end

	def delete
		@quiniela = Quiniela.find(params[:id])
		if @quiniela.user.id == current_user.id
			@quiniela.destroy
		end
		redirect_back_or_default root_url
	end

	def edit
		@quiniela = Quiniela.find(params[:id])
	end

	def update
		@quiniela = Quiniela.find(params[:id])
		if @quiniela.update_attributes(params[:quiniela])
      		flash[:success] = "Quiniela modificada exitosamente."
			redirect_back_or_default root_url
		else
		    render 'edit'
		end
	end

	def show
		@quiniela = Quiniela.find(params[:id])
	end

end
