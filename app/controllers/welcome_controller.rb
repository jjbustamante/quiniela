class WelcomeController < ApplicationController
  def index
  	@session = current_user

  end

  before_filter :authenticate_user!
end
