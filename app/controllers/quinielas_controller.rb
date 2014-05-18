class QuinielasController < ApplicationController
	def new
	end

	def create
			render plain: params[:quiniela].inspect
	end
end
