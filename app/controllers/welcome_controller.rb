class WelcomeController < ApplicationController
  def index
  	@quinielas = Quiniela.where('').order("points DESC").limit(3)
	@active_ranking = 'active'
	@session = current_user
	@no_top = true
	if !@session
		redirect_to "/"
	end
  end

  before_filter :authenticate_user!
end
